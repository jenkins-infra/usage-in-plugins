final String cron_string = env.BRANCH_IS_PRIMARY ? 'H H * * *' : ''

pipeline {
   agent {
      label 'maven-21'
   }

   triggers {
      cron(cron_string)
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
