node {
  def mvnHome = tool 'Maven-3.9'
  env.JAVA_HOME = tool 'JDK-21'
  
  stage('Checkout') {
    checkout scm
    echo "Branch $env.BRANCH_NAME"
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
    pom = readMavenPom file: 'pom.xml'
    sh "${mvnHome}/bin/mvn sonar:sonar -DskipTests -Dsonar.java.coveragePlugin=jacoco -Dsonar.jacoco.reportPath=target/jacoco.exec -Dsonar.host.url=https://www.x1/sonar -Dsonar.projectKey=${pom.groupId}:${pom.artifactId}:$env.BRANCH_NAME -Dsonar.projectName=${pom.artifactId}:$env.BRANCH_NAME"
  }
  
  stage('dependencyTrack') {
    withCredentials([string(credentialsId: 'dtrack', variable: 'API_KEY')]) {
      dependencyTrackPublisher artifact: 'target/bom.xml', projectName: ${pom.artifactId}, projectVersion: ${pom.version}, synchronous: true, dependencyTrackApiKey: API_KEY, projectProperties: [group: ${pom.groupId}]
    }
  }
}
