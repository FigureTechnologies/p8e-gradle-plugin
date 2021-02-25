@Library('jenkins-pipeline') _

pipeline {
    agent any

    tools {
        jdk 'JDK11'
    }

    stages {
        stage('Stage Checkout') {
            steps {
                gitCheckout()
            }
        }
        stage('Gradle Build') {
            steps {
                gradleCleanBuild("")
            }
        }
        stage('Gradle Publish') {
            steps {
                script {
                    gradlePublish("")
                }
            }
        }
    }
}
