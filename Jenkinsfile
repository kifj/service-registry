node {
  def mvnHome = tool 'Maven-3.6'
  
  stage('Checkout') {
    git url: 'https://github.com/kifj/service-registry.git', branch: 'wildfly-18'
  }
  
  stage('Build') {
    sh "${mvnHome}/bin/mvn clean package"
  }
  
  stage('Publish') {
    sh "${mvnHome}/bin/mvn deploy site-deploy -DskipTests"
  }

  stage('Sonar') {
    sh "${mvnHome}/bin/mvn sonar:sonar -DskipTests -Dsonar.java.coveragePlugin=jacoco -Dsonar.jacoco.reportPath=target/jacoco.exec -Dsonar.host.url=https://www.x1/sonar -Dsonar.projectKey=x1.wildfly:service-registry:wildfly-18"
  }
}
