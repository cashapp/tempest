/*
 * Copyright 2021 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.tempest2.testing

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer
import com.google.common.util.concurrent.AbstractIdleService
import java.io.File

class JvmDynamoDbServer private constructor(
  override val port: Int
) : AbstractIdleService(), TestDynamoDbServer {

  override val id = "tempest2-jvm-dynamodb-local-$port"

  private lateinit var server: DynamoDBProxyServer

  override fun startUp() {
    val libraryFile = libsqlite4javaNativeLibrary()
    System.setProperty("sqlite4java.library.path", libraryFile.parent)

    server = ServerRunner.createServerFromCommandLineArgs(
      arrayOf("-inMemory", "-port", port.toString())
    )
    server.start()
  }

  private fun libsqlite4javaNativeLibrary(): File {
    val prefix = libsqlite4javaPrefix()
    val classpath = System.getProperty("java.class.path")
    val classpathElements = classpath.split(File.pathSeparator)
    for (element in classpathElements) {
      val file = File(element)
      if (file.name.startsWith(prefix)) {
        return file
      }
    }
    throw IllegalArgumentException("couldn't find native library for $prefix")
  }

  /**
   * Returns the prefix of the sqlite4java native library for the current platform.
   *
   * Observed values of os.arch include:
   *  * x86_64
   *  * amd64
   *
   * Observed values of os.name include:
   *  * Linux
   *  * Mac OS X
   *
   * Available native versions of sqlite4java are:
   *  * libsqlite4java-linux-amd64-1.0.392.so
   *  * libsqlite4java-linux-i386-1.0.392.so
   *  * libsqlite4java-osx-1.0.392.dylib
   *  * sqlite4java-win32-x64-1.0.392.dll
   *  * sqlite4java-win32-x86-1.0.392.dll
   */
  private fun libsqlite4javaPrefix(): String {
    val osArch = System.getProperty("os.arch")
    val osName = System.getProperty("os.name")

    return when {
      osName == "Linux" && osArch == "amd64" -> "libsqlite4java-linux-amd64-"
      osName == "Mac OS X" && osArch == "x86_64" -> "libsqlite4java-osx-"
      else -> throw IllegalStateException("unexpected platform: os.name=$osName os.arch=$osArch")
    }
  }

  override fun shutDown() {
    server.stop()
  }

  object Factory : TestDynamoDbServer.Factory<JvmDynamoDbServer> {
    override fun create(port: Int) = JvmDynamoDbServer(port)
  }
}
