/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.java

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import java.util.stream.Collectors

class JavaApplicationOutgoingVariantsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        def repo = mavenRepo
        repo.module("test", "compile-only", "1.0").publish()
        repo.module("test", "implementation", "1.0").publish()
        repo.module("test", "runtime-only", "1.0").publish()

        createDirs("other-java", "java", "consumer")
        settingsFile << "include 'other-java', 'java', 'consumer'"
        buildFile << """
def artifactType = Attribute.of('artifactType', String)

allprojects {
    repositories { maven { url '${mavenRepo.uri}' } }
}

project(':other-java') {
    apply plugin: 'java-library'
}

project(':java') {
    apply plugin: 'application'
    dependencies {
        implementation project(':other-java')
        implementation files('file-dep.jar')
        compileOnly 'test:compile-only:1.0'
        implementation 'test:implementation:1.0'
        runtimeOnly 'test:runtime-only:1.0'
    }
}

project(':consumer') {
    configurations { consume }
    dependencies { consume project(':java') }
    task resolve {
        inputs.files configurations.consume
        def fileNames = provider {
            configurations.consume.files.collect { it.name }
        }
        def incomingArtifacts = provider {
            configurations.consume.incoming.artifacts.collect { "\$it.id \$it.variant.attributes" }
        }
        doLast {
            println "files: " + fileNames.get()
            incomingArtifacts.get().each {
                println it
            }
        }
    }
}
"""
    }

    private resolve() {
        succeeds "resolve"
    }

    def "provides runtime JAR as default variant without jvm-ecosystem plugin"() {
        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        assertResolveOutput("""
            files: [java.jar, file-dep.jar, other-java.jar, implementation-1.0.jar, runtime-only-1.0.jar]
            java.jar (project :java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            file-dep.jar {artifactType=jar}
            other-java.jar (project :other-java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            implementation-1.0.jar (test:implementation:1.0) {artifactType=jar, org.gradle.status=release}
            runtime-only-1.0.jar (test:runtime-only:1.0) {artifactType=jar, org.gradle.status=release}
        """)
    }

    def "provides runtime JAR as default variant with jvm-ecosystem plugin"() {
        buildFile << """
            project(':consumer') {
                apply plugin: 'jvm-ecosystem'
            }
        """

        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        assertResolveOutput("""
            files: [java.jar, file-dep.jar, other-java.jar, implementation-1.0.jar, runtime-only-1.0.jar]
            java.jar (project :java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            file-dep.jar {artifactType=jar, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            other-java.jar (project :other-java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            implementation-1.0.jar (test:implementation:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}
            runtime-only-1.0.jar (test:runtime-only:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}
        """)
    }

    def "provides API compile jar variant - requestViewAttribute: #requestViewAttribute"() {
        buildFile << """
            project(':consumer') {
                apply plugin: 'jvm-ecosystem'
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                if ($requestViewAttribute) {
                    configurations.consume.attributes.attribute(ApiType.TYPE_ATTRIBUTE, objects.named(ApiType, ApiType.PUBLIC))
                }
            }
        """

        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        assertResolveOutput("""
            files: [java.jar]
            java.jar (project :java) {artifactType=jar, org.gradle.api-type=public, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-api}
        """)

        where:
        requestViewAttribute << [true, false]
    }

    def "provides API compile classes variant - requestViewAttribute: #requestViewAttribute"() {
        buildFile << """
            project(':consumer') {
                apply plugin: 'jvm-ecosystem'
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                configurations.consume.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.CLASSES))
                if ($requestViewAttribute) {
                    configurations.consume.attributes.attribute(ApiType.TYPE_ATTRIBUTE, objects.named(ApiType, ApiType.PUBLIC))
                }
            }
        """

        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        assertResolveOutput("""
            files: [java.jar]
            java.jar (project :java) {artifactType=jar, org.gradle.api-type=public, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-api}
        """)

        where:
        requestViewAttribute << [true, false]
    }

    def "provides implementation compile JAR variant - requestJarAttribute: #requestJarAttribute"() {
        buildFile << """
            project(':consumer') {
                apply plugin: 'jvm-ecosystem'
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                configurations.consume.attributes.attribute(ApiType.TYPE_ATTRIBUTE, objects.named(ApiType, ApiType.PRIVATE))
                if ($requestJarAttribute) {
                    configurations.consume.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                }
            }
        """

        when:
        resolve()

        then:
        result.assertTasksExecuted(":java:classes", ":java:compileJava", ":java:jar", ":java:processResources", ":other-java:classes", ":other-java:compileJava", ":other-java:jar", ":other-java:processResources", ":consumer:resolve")
        assertResolveOutput("""
            files: [java.jar, file-dep.jar, compile-only-1.0.jar, other-java.jar, implementation-1.0.jar]
            java.jar (project :java) {artifactType=jar, org.gradle.api-type=private, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-api}
            file-dep.jar {artifactType=jar, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            compile-only-1.0.jar (test:compile-only:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-api}
            other-java.jar (project :other-java) {artifactType=jar, org.gradle.api-type=private, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-api}
            implementation-1.0.jar (test:implementation:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-api}
        """)

        where:
        requestJarAttribute << [true, false]
    }

    def "provides implementation compile classes and resources variant"() {
        buildFile << """
            project(':consumer') {
                apply plugin: 'jvm-ecosystem'
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                configurations.consume.attributes.attribute(ApiType.TYPE_ATTRIBUTE, objects.named(ApiType, ApiType.PRIVATE))
                configurations.consume.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.${requestedElements}))
            }
        """

        when:
        resolve()

        then:
        result.assertTasksExecuted(":consumer:resolve", ":java:classes", ":java:compileJava", ":java:processResources", ":other-java:classes", ":other-java:compileJava", ":other-java:processResources")
        assertResolveOutput("""
            files: [main, main, file-dep.jar, compile-only-1.0.jar, main, main, implementation-1.0.jar]
            main (project :java) {artifactType=directory, org.gradle.api-type=private, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes+resources, org.gradle.usage=java-api}
            main (project :java) {artifactType=directory, org.gradle.api-type=private, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes+resources, org.gradle.usage=java-api}
            file-dep.jar {artifactType=jar, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            compile-only-1.0.jar (test:compile-only:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-api}
            main (project :other-java) {artifactType=directory, org.gradle.api-type=private, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes+resources, org.gradle.usage=java-api}
            main (project :other-java) {artifactType=directory, org.gradle.api-type=private, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes+resources, org.gradle.usage=java-api}
            implementation-1.0.jar (test:implementation:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-api}
        """)

        where:
        requestedElements << ["CLASSES", "RESOURCES", "CLASSES_AND_RESOURCES"]
    }

    def "provides runtime jar variant - requestJarAttribute: #requestJarAttribute"() {
        buildFile << """
            project(':consumer') {
                apply plugin: 'jvm-ecosystem'
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                if ($requestJarAttribute) {
                    configurations.consume.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                }
            }
        """

        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        assertResolveOutput("""
            files: [java.jar, file-dep.jar, other-java.jar, implementation-1.0.jar, runtime-only-1.0.jar]
            java.jar (project :java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            file-dep.jar {artifactType=jar, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            other-java.jar (project :other-java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            implementation-1.0.jar (test:implementation:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}
            runtime-only-1.0.jar (test:runtime-only:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}
        """)

        where:
        requestJarAttribute << [true, false]
    }

    def "provides runtime JAR variant using artifactType attribute"() {
        buildFile << """
            project(':consumer') {
                apply plugin: 'jvm-ecosystem'
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                configurations.consume.attributes.attribute(artifactType, ArtifactTypeDefinition.JAR_TYPE)
            }
        """

        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":other-java:processResources", ":other-java:classes", ":other-java:jar", ":java:compileJava", ":java:processResources", ":java:classes", ":java:jar", ":consumer:resolve")
        assertResolveOutput("""
            files: [java.jar, file-dep.jar, other-java.jar, implementation-1.0.jar, runtime-only-1.0.jar]
            java.jar (project :java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            file-dep.jar {artifactType=jar, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            other-java.jar (project :other-java) {artifactType=jar, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            implementation-1.0.jar (test:implementation:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}
            runtime-only-1.0.jar (test:runtime-only:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}
        """)
    }

    def "provides runtime classes variant"() {
        buildFile << """
            project(':consumer') {
                apply plugin: 'jvm-ecosystem'
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                configurations.consume.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.CLASSES))
            }
        """

        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:compileJava", ":java:compileJava", ":consumer:resolve")
        assertResolveOutput("""
            files: [main, file-dep.jar, main, implementation-1.0.jar, runtime-only-1.0.jar]
            main (project :java) {artifactType=java-classes-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes, org.gradle.usage=java-runtime}
            file-dep.jar {artifactType=jar, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            main (project :other-java) {artifactType=java-classes-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=classes, org.gradle.usage=java-runtime}
            implementation-1.0.jar (test:implementation:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}
            runtime-only-1.0.jar (test:runtime-only:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}
        """)
    }

    def "provides runtime resources variant"() {
        buildFile << """
            project(':consumer') {
                apply plugin: 'jvm-ecosystem'
                configurations.consume.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                configurations.consume.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.RESOURCES))
            }
        """

        when:
        resolve()

        then:
        result.assertTasksExecuted(":other-java:processResources", ":java:processResources", ":consumer:resolve")
        assertResolveOutput("""
            files: [main, file-dep.jar, main, implementation-1.0.jar, runtime-only-1.0.jar]
            main (project :java) {artifactType=java-resources-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=resources, org.gradle.usage=java-runtime}
            file-dep.jar {artifactType=jar, org.gradle.libraryelements=jar, org.gradle.usage=java-runtime}
            main (project :other-java) {artifactType=java-resources-directory, org.gradle.category=library, org.gradle.dependency.bundling=external, ${defaultTargetPlatform()}, org.gradle.libraryelements=resources, org.gradle.usage=java-runtime}
            implementation-1.0.jar (test:implementation:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}
            runtime-only-1.0.jar (test:runtime-only:1.0) {artifactType=jar, org.gradle.category=library, org.gradle.libraryelements=jar, org.gradle.status=release, org.gradle.usage=java-runtime}
        """)
    }

    static String defaultTargetPlatform() {
        "org.gradle.jvm.version=${JavaVersion.current().majorVersion}"
    }

    void assertResolveOutput(String output) {
        result.groupedOutput.task(":consumer:resolve").assertOutputContains(
            Arrays.stream(output.split("\n"))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .collect(Collectors.joining(System.lineSeparator()))
        )
    }
}
