# maven-gav-checker
Utility for checking if a Maven dependency GAV is available in a particular Maven repository, or searching for available versions of a dependency in a particular repository.

This is mostly useful if you are building a project which has a requirement that its build dependencies are available in some private or more restricted Maven repository
than the usual Maven Central, or if you'd like to be able to cross-reference available versions for a dependency between Maven repositories (for project upstream and
downstream build differences, for example).

The default search repository is `https://repo.maven.apache.org/maven2/`, but this can be overridden with the `-r`/`--repository` flag.

Given a dependency `groupId:artifactId` argument, the tool will print out a list of all of the available versions for that dependency in the search repository. The
output can be limited with the `-n`/`--limit` flag.

Given a dependency `groupId:artifactId:version` argument, the tool will print whether this exact GAV is available in the search repository and indicate it in the exit
status. If the dependency is not available it will also list available versions.

Given a GitHub Pull Request URL, the tool will attempt to use the [`gh`](https://github.com/cli/cli) tool to get the Pull Request title. If this meets the expected Dependabot
title format, the tool will extract the GAV from the title and act as if that GAV were specified directly.

Given a GitHub repository URL, the tool will attempt to use `gh` to get the repository's `pom.xml` from its default branch. Then it will use `mvn` to resolve all of the
project dependencies, and report on this list of GAVs.

## Building

`./mvnw clean package`

## Running

Get the executable, either by building it locally or by downloading [a GitHub release](https://github.com/andrewazores/maven-gav-checker/releases).

Then:

```bash
$ ./target/maven-gav-checker-*-runner -h
2024-08-12 16:41:15,582 INFO  [io.quarkus] (main) maven-gav-checker 1.0.0-SNAPSHOT native (powered by Quarkus 3.13.2) started in 0.002s. 
2024-08-12 16:41:15,582 INFO  [io.quarkus] (main) Profile prod activated. 
2024-08-12 16:41:15,582 INFO  [io.quarkus] (main) Installed features: [cdi, picocli]
Usage: GAVFind [-hkV] [-n=<count>] [-r=<repoRoot>] <gav>
Check Maven dependencies availability in a particular Maven repository
      <gav>             The Maven dependency GroupId:ArtifactId[:Version]
                          (GAV), ex. org.slf4j:slf4j-api:2.0.12 or info.picocli:
                          picocli . If no version is specified (version listing
                          mode) then all available versions are printed,
                          otherwise the existence of the specified version is
                          checked. If the GitHub 'gh' client is installed, this
                          can also be a GitHub Pull Request URL and the PR
                          title will be used to infer the GAV.
  -h, --help            Show this help message and exit.
  -k, --insecure        Disable TLS validation on the remote Maven repository.
  -n, --limit=<count>   The number of release versions to list in version
                          listing mode. Defaults to the full list.
  -r, --repository=<repoRoot>
                        The Maven repository root URL to search, ex. https:
                          //repo.maven.apache.org/maven2/ .
  -V, --version         Print version information and exit.
2024-08-12 16:41:15,583 INFO  [io.quarkus] (main) maven-gav-checker stopped in 0.000s

$ ./target/maven-gav-checker-*-runner org.slf4j:slf4j-api -n5 -r https://repo.maven.apache.org/maven2
2024-08-12 16:40:52,359 INFO  [io.quarkus] (main) maven-gav-checker 1.0.0-SNAPSHOT native (powered by Quarkus 3.13.2) started in 0.002s. 
2024-08-12 16:40:52,359 INFO  [io.quarkus] (main) Profile prod activated. 
2024-08-12 16:40:52,359 INFO  [io.quarkus] (main) Installed features: [cdi, picocli]
2024-08-12 16:40:52,619 INFO  [com.git.and.GAVFind] (main) 
latest: 2.1.0-alpha1
release: 2.1.0-alpha1
available:
	2.1.0-alpha1
	2.1.0-alpha0
	2.0.16
	2.0.15
	2.0.14
2024-08-12 16:40:52,620 INFO  [io.quarkus] (main) maven-gav-checker stopped in 0.000s
```
