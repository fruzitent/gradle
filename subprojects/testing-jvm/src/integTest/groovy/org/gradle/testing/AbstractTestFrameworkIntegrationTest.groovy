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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

abstract class AbstractTestFrameworkIntegrationTest extends AbstractIntegrationSpec {
    abstract void createPassingFailingTest()

    abstract String getTestTaskName()

    abstract String getPassingTestCaseName()
    abstract String getFailingTestCaseName()

    def "can listen for test results"() {
        given:
        createPassingFailingTest()

        buildFile << """
            def listener = new TestListenerImpl()
            ${testTaskName}.addTestListener(listener)
            ${testTaskName}.ignoreFailures = true
            class TestListenerImpl implements TestListener {
                void beforeSuite(TestDescriptor suite) { println "START Test Suite [\$suite.className] [\$suite.name]" }
                void afterSuite(TestDescriptor suite, TestResult result) { println "FINISH Test Suite [\$suite.className] [\$suite.name] [\$result.resultType] [\$result.testCount]" }
                void beforeTest(TestDescriptor test) { println "START Test Case [\$test.className] [\$test.name]" }
                void afterTest(TestDescriptor test, TestResult result) { println "FINISH Test Case [\$test.className] [\$test.name] [\$result.resultType] [\$result.testCount]" }
            }
        """

        when:
        succeeds "check"

        then:

        outputContains "START Test Suite [null] [Gradle Test Run :$testTaskName]"
        outputContains "FINISH Test Suite [null] [Gradle Test Run :$testTaskName] [FAILURE] [2]"

        outputContains "START Test Suite [SomeOtherTest] [SomeOtherTest]"
        outputContains "FINISH Test Suite [SomeOtherTest] [SomeOtherTest]"
        outputContains "START Test Case [SomeOtherTest] [$passingTestCaseName]"
        outputContains "FINISH Test Case [SomeOtherTest] [$passingTestCaseName] [SUCCESS] [1]"

        outputContains "START Test Suite [SomeTest] [SomeTest]"
        outputContains "FINISH Test Suite [SomeTest] [SomeTest]"
        outputContains "START Test Case [SomeTest] [$failingTestCaseName]"
        outputContains "FINISH Test Case [SomeTest] [$failingTestCaseName] [FAILURE] [1]"
    }
}
