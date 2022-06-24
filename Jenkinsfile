node {
  def mvnHome = tool 'Maven-3.8'
  def branch = 'wildfly-26'
  
  stage('Checkout') {
    checkout scm
  }
  
  stage('Build') {
    try {
      sh "${mvnHome}/bin/mvn -Punpack-wildfly,arq-managed clean package -Dx1.service.registry.etcd=http://etcd.x1:4001
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
}
