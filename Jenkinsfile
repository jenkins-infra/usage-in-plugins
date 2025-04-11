pipeline {
   agent {
      // JDK8 - https://github.com/jenkins-infra/usage-in-plugins/blob/6e73d80e0dc5384fed2f627d0b2d56e11ba46f1c/pom.xml#L97-L98
      label 'maven-8'
   }

   triggers {
      cron('H H * * *')
   }

   options {
      disableConcurrentBuilds()
      buildDiscarder(logRotator(daysToKeepStr: '90'))
      timeout(time: 1, unit: 'HOURS')
   }

   stages {
      stage ('Checkout') {
         steps {
            checkout scm
         }
      }

      stage ('Build') {
         steps {
            sh 'mvn clean package exec:java'
         }
      }

      stage ('Archive') {
         steps {
            archive 'output/**'
         }
      }
   }
}
