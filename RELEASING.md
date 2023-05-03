Releasing
=========

 1. Change the version in `gradle.properties` and `mkdocs.yml` to a non-SNAPSHOT verson.
 2. Update the `CHANGELOG.md` for the impending release.
 3. Update the `README.md` with the new version.
 4. `git commit -am "Prepare for release X.Y.Z."` (where X.Y.Z is the new version)
 5. `git tag -a X.Y.Z -m "Version X.Y.Z"` (where X.Y.Z is the new version)
 6. Update the `gradle.properties` to the next SNAPSHOT version.
 7. `git commit -am "Prepare next development version."`
 8. `git push && git push --tags`
 9. Wait until the "Publish a release" action completes, then visit [Sonatype Nexus](https://oss.sonatype.org/) and promote the artifacts.
 10. Update the sample app to the release version and send a PR.
 11. Draft a new [release](https://docs.github.com/en/github/administering-a-repository/managing-releases-in-a-repository) of `X.Y.Z` to trigger the "Publish the mkdocs to gh-pages" action.
 
If the github action fails, drop the artifacts from Sonatype and re run the job. You might need to
delete the plugin off the JetBrains plugin portal first if the ubuntu job which publishes it
already succeeded.
