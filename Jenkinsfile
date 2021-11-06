node {
  def mvnHome = tool 'Maven-3.6'
  env.JAVA_HOME = tool 'JDK-1.8'
  
  stage('Checkout') {
    checkout scm
  }
  
  stage('Build') {
    sh "${mvnHome}/bin/mvn clean package"
  }
  
  stage('Publish') {
    sh "${mvnHome}/bin/mvn deploy site-deploy -DskipTests"
  }

  stage('Sonar') {
    withEnv(["JAVA_HOME=${tool 'JJDK-11'}"]) {
      sh "${mvnHome}/bin/mvn sonar:sonar -DskipTests -Dsonar.java.coveragePlugin=jacoco -Dsonar.jacoco.reportPath=target/jacoco.exec -Dsonar.host.url=https://www.x1/sonar"
    }
  }
}
