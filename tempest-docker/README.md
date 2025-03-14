# tempest-docker

A helper class which attempts to fetch docker registry credentials via the credential store defined
in `$HOME/.docker/config.json`.

This is necessary for pulling images that require authentication.

# Basic Usage

```kotlin
import app.cash.tempest.docker.withLocalDockerCredentials

val defaultDockerClientConfig = DefaultDockerClientConfig
    .createDefaultConfigBuilder()
    .withLocalDockerCredentials()
    .build()
```

# Basic Usage with custom registry URL

```kotlin
import app.cash.tempest.docker.withLocalDockerCredentials

val defaultDockerClientConfig = DefaultDockerClientConfig
    .createDefaultConfigBuilder()
    .withLocalDockerCredentials("https://custom.registry.url/v1/")
    .build()
```

# Advanced Usage

```kotlin
import app.cash.tempest.docker.withLocalDockerCredentials

val credentials = DockerCredentials.getDockerCredentials("https://index.docker.io/v1/")

val defaultDockerClientConfig = DefaultDockerClientConfig
    .createDefaultConfigBuilder()
    // Set the retrieved username and password
    .withRegistryUsername(credentials?.username)
    .withRegistryPassword(credentials?.password)
    .build()
```