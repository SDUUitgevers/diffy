#!/usr/bin/env groovy


pipeline {
    agent any
    environment {
        ARTIFACTORY = credentials('artifactory-k8s')
    }
    stages {
        stage('init') {
            steps {
                script {
                    def sbtHome = tool 'sbt'
                    env.sbt= "${sbtHome}/bin/sbt -batch -DsduTeam=cwc 'set credentials += Credentials(\"Artifactory Realm\", \"artifactory.k8s.awssdu.nl\", \"${env.ARTIFACTORY_USR}\", \"${env.ARTIFACTORY_PSW}\")'"
                }
            }
        }
        stage('PR') {
            when {
                expression { BRANCH_NAME ==~ /^PR-\d+$/ }
            }
            steps {
                ansiColor('xterm') {
                    sh "$sbt clean test"
                }
            }
            post {
                always {
                    junit "target/test-reports/*.xml"
                }
            }
        }
        stage('QA') {
            when {
                branch 'qa'
            }
            steps {
                ansiColor('xterm') {
                    sh "$sbt update clean publish"
                }
            }
        }
        stage('Master') {
            when {
                branch 'master'
            }
            steps {
                ansiColor('xterm') {
                    sh "$sbt clean coverage test coverageReport"
                }
            }
            post {
                always {
                    junit "target/test-reports/*.xml"
                    step([$class: 'CoberturaPublisher', autoUpdateHealth: false, autoUpdateStability: false,
                          coberturaReportFile: 'target/scala-2.11/coverage-report/cobertura.xml', failNoReports: false,
                          failUnhealthy: false, failUnstable: false, maxNumberOfBuilds: 0, onlyStable: false,
                          sourceEncoding: 'ASCII', zoomCoverageChart: false])
                }
            }
        }
    }
}
