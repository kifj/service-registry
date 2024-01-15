node {
  def mvnHome = tool 'Maven-3.9'
  env.JAVA_HOME = tool 'JDK-21'
  def branch = 'wildfly-30'
  
  stage('Checkout') {
    checkout scm
  }
  
  stage('Build') {
    try {
      sh "${mvnHome}/bin/mvn -Punpack-wildfly,arq-managed clean package"
    } finally {
      junit '**/target/surefire-reports/TEST-*.xml'
      jacoco(execPattern: '**/**.exec')
    }
  }
  
  stage('Publish') {
    sh "${mvnHome}/bin/mvn deploy site-deploy -DskipTests"
  }

  stage('Sonar') {
    sh "${mvnHome}/bin/mvn sonar:sonar -DskipTests -Dsonar.java.coveragePlugin=jacoco -Dsonar.jacoco.reportPath=target/jacoco.exec -Dsonar.host.url=https://www.x1/sonar -Dsonar.projectKey=x1.wildfly:service-registry:${branch} -Dsonar.projectName=service-registry:${branch}"
  }
  
  stage('dependencyTrack') {
    withCredentials([string(credentialsId: 'dtrack', variable: 'API_KEY')]) {
      dependencyTrackPublisher artifact: 'target/bom.xml', projectName: 'service-registry', projectVersion: '1.3', synchronous: true, dependencyTrackApiKey: API_KEY, projectProperties: [group: 'x1.wildfly']
    }
  }
}
