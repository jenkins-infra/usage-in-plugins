pipeline {
   agent {
      label 'maven'
   }

   if (env.BRANCH_IS_PRIMARY) {
      properties([pipelineTriggers([cron('H H * * *')])])
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
