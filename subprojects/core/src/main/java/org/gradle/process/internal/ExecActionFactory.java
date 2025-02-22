/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.Global.class)
public interface ExecActionFactory {
    /**
     * Creates an {@link ExecAction} that is not decorated. Use this when the action is not made visible to the DSL.
     * If you need to make the action visible to the DSL, use {@link org.gradle.process.ExecOperations} instead.
     */
    ExecAction newExecAction();

    /**
     * Creates a {@link JavaExecAction} that is not decorated. Use this when the action is not made visible to the DSL.
     * If you need to make the action visible to the DSL, use {@link org.gradle.process.ExecOperations} instead.
     */
    JavaExecAction newJavaExecAction();
}
