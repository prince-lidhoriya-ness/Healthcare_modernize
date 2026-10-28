// CI/CD: Build -> Start app -> Backend tests -> E2E tests -> Quality Gates (gates 1-4) -> Deliver.
// Deliver only runs when every enforced gate meets quality-gates/thresholds.yaml.
// Everything runs in containers on the built-in node; see README section 8 for the Jenkins setup.
pipeline {
    agent {
        label 'built-in'
    }
    parameters {
        choice(name: 'GATE_PHASE', choices: ['after', 'before'],
               description: 'after = ENFORCED (build fails on any missed threshold). before = BASELINE (records numbers, never fails).')
        string(name: 'GATES', defaultValue: '1,2,3,4',
               description: '1 static/security (SonarQube, Semgrep, Trivy), 2 secrets/PII, 3 Claude cost, 4 functional evals')
        booleanParam(name: 'SKIP_SONARQUBE', defaultValue: false,
                     description: 'Fast mode: skip SonarQube (quick tests only, not for the real pipeline)')
        string(name: 'CORPORATE_CA_CREDENTIAL', defaultValue: '',
               description: 'ID of a "Secret file" credential with the corporate TLS-inspection CA (e.g. Netskope). Empty = system CAs only.')
    }
    options {
        timeout(time: 90, unit: 'MINUTES')      // first run downloads Maven/npm deps, SonarQube and the Trivy DB
        disableConcurrentBuilds()               // fixed container names and ports, one SonarQube
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }
    triggers {
        pollSCM '* * * * *'
    }
    environment {
        APP = 'ivirma-clinic-app'                 // compose project name -> network ivirma-clinic-app_default
        APP_NET = 'ivirma-clinic-app_default'
        MAVEN_IMAGE = 'maven:3.9.5-eclipse-temurin-21'
        PLAYWRIGHT_IMAGE = 'mcr.microsoft.com/playwright:v1.63.0-noble'
        CURL_IMAGE = 'curlimages/curl:8.10.1'
    }
    stages {
        stage('Build') {
            steps {
                sh 'docker compose -p "$APP" build'
            }
        }
        stage('Start app') {
            steps {
                sh '''
                docker compose -p "$APP" down -v --remove-orphans || true
                docker compose -p "$APP" up -d
                docker run --rm --network "$APP_NET" "$CURL_IMAGE" -fsS --retry 60 --retry-delay 3 --retry-all-errors \
                    http://backend:8080/actuator/health
                '''
            }
        }
        stage('Backend tests') {
            // README 5.3: unit + REST API tests, the PostgreSQL persistence test and the JaCoCo report
            steps {
                sh '''
                set +x
                export TEST_DB_PASSWORD="$(docker exec ivirma-db printenv POSTGRES_PASSWORD)"
                set -x
                docker run --rm --network "$APP_NET" \
                    -e TEST_DB_URL=jdbc:postgresql://db:5432/ivirma_clinic -e TEST_DB_USER=ivirma_app -e TEST_DB_PASSWORD \
                    -v "$WORKSPACE/backend:/app" -v ivirma-m2:/root/.m2 -w /app "$MAVEN_IMAGE" mvn -B verify
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'backend/target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }
        stage('E2E tests') {
            // README 5.4: the Playwright regression suite that must keep passing after any upgrade
            steps {
                sh '''
                docker run --rm --ipc=host --network "$APP_NET" -e BASE_URL=http://frontend \
                    -v "$WORKSPACE/e2e:/e2e" -v /e2e/node_modules -w /e2e "$PLAYWRIGHT_IMAGE" \
                    sh -c "npm ci && npx playwright test"
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'e2e/playwright-report/**, e2e/test-results/**', allowEmptyArchive: true
                }
            }
        }
        stage('Quality Gates') {
            environment {
                SKIP_SONAR = "${params.SKIP_SONARQUBE ? '1' : '0'}"     // run_gates.sh expects 1/0, not true/false
            }
            steps {
                script {
                    env.QG_GATES = params.GATES.tokenize(',').collect { it.trim() }.findAll { it }.join(',')

                    // the CA files are git-ignored, so a fresh checkout has none; runner/Dockerfile, Semgrep and Trivy
                    // all need one. Build it from the system CAs plus the optional corporate CA.
                    def prepareCerts = '''
                        mkdir -p quality-gates/certs
                        cat /etc/ssl/certs/ca-certificates.crt ${CORP_CA:+"$CORP_CA"} > quality-gates/certs/corporate-ca.pem
                        cp quality-gates/certs/corporate-ca.pem quality-gates/runner/corporate-ca.pem
                    '''
                    if (params.CORPORATE_CA_CREDENTIAL?.trim()) {
                        withCredentials([file(credentialsId: params.CORPORATE_CA_CREDENTIAL.trim(), variable: 'CORP_CA')]) {
                            sh prepareCerts
                        }
                    } else {
                        sh prepareCerts
                    }

                    // Scan a copy of the app without the gates toolkit, git data, build output and test reports.
                    // backend/target/classes stays: SonarQube's Java analysis needs the compiled classes.
                    sh '''
                    rm -rf .gate-src && mkdir .gate-src
                    tar --exclude=./.git --exclude=./.gate-src --exclude=./quality-gates --exclude=node_modules \
                        --exclude=./backend/target --exclude=./e2e/test-results --exclude=./e2e/playwright-report \
                        -cf - . | tar -xf - -C .gate-src
                    if [ -d backend/target/classes ]; then
                        mkdir -p .gate-src/backend/target && cp -r backend/target/classes .gate-src/backend/target/
                    fi
                    '''

                    // gate 4 calls the app started in 'Start app' (quality-gates/evals/services.yaml)
                    def rc = sh(returnStatus: true, script: '''
                        export TARGET_DIR="$WORKSPACE/.gate-src"
                        bash quality-gates/run_gates.sh "$GATE_PHASE" "$QG_GATES"
                    ''')

                    // results/ is git-ignored and survives in the workspace, so a baseline from an earlier
                    // `before` build is compared against every enforced run
                    sh '''
                    if [ "$GATE_PHASE" != before ] && [ -f quality-gates/results/before/summary.json ]; then
                        bash quality-gates/run_gates.sh compare before "$GATE_PHASE" && cat quality-gates/results/comparison.md
                    fi
                    cat "quality-gates/results/$GATE_PHASE/feedback.md" || true
                    '''

                    if (rc == 1) {
                        error "Quality gates FAILED (phase ${params.GATE_PHASE}) - see feedback.md in the build artifacts"
                    } else if (rc != 0) {
                        error "Quality gates setup error (exit ${rc}) - is the docker daemon reachable and does it mount jenkins-data?"
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'quality-gates/results/**',
                                     excludes: 'quality-gates/results/.sonar_token',
                                     allowEmptyArchive: true
                }
            }
        }
        stage('Deliver') {
            steps {
                echo 'Deliver....'
                // images built in 'Build': ivirma-clinic-app-backend, ivirma-clinic-app-frontend.
                // Tag and push them to your registry here, e.g.
                //   docker tag ivirma-clinic-app-backend <registry>/ivirma-backend:$BUILD_NUMBER && docker push ...
                sh 'docker image ls --filter "reference=$APP-*"'
            }
        }
    }
    post {
        always {
            sh '''
            docker compose -p "$APP" down -v --remove-orphans || true
            # containers write target/, test reports and gate results as root; hand them back to the jenkins user
            docker run --rm --entrypoint chown -v "$WORKSPACE:/w" "$MAVEN_IMAGE" -R "$(id -u):$(id -g)" /w || true
            '''
        }
    }
}
