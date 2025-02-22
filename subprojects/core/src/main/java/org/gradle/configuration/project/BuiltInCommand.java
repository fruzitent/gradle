/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configuration.project;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.List;

/**
 * Provides metadata about a build-in command, which is a task-like action (usually backed by an actual task)
 * that can be invoked from the command-line, such as `gradle init ...` or `gradle help ...`
 *
 * A built-in command can be invoked from any directory, and does not require a Gradle build definition to be present.
 */
@ServiceScope(Scopes.Global.class)
public interface BuiltInCommand {
    /**
     * Returns the list of task paths that should be used when none are specified by the user. Returns an empty list if this command should not be used as a default.
     */
    List<String> asDefaultTask();

    /**
     * Does the given list of task paths reference this command?
     */
    boolean commandLineMatches(List<String> taskNames);
}
