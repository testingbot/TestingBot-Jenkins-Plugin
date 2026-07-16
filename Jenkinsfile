/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
buildPlugin(
  forkCount: '1C', // run this number of tests in parallel for faster feedback
  useContainerAgent: true, // set to false if you need to use a full VM
  configurations: [
    [platform: 'linux', jdk: 21],
    [platform: 'windows', jdk: 17],
  ]
)
