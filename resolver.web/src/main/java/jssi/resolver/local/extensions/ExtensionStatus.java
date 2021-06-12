/*
 *
 *  * Copyright 2021 UBICUA.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package jssi.resolver.local.extensions;

public class ExtensionStatus {

    public static final ExtensionStatus DEFAULT = new ExtensionStatus(false, false, false);

    public static final ExtensionStatus SKIP_EXTENSION_BEFORE = new ExtensionStatus(true, false, false);
    public static final ExtensionStatus SKIP_DRIVER = new ExtensionStatus(false, true, false);
    public static final ExtensionStatus SKIP_EXTENSIONS_AFTER = new ExtensionStatus(false, false, true);

    private boolean skipExtensionsBefore;
    private boolean skipDriver;
    private boolean skipExtensionsAfter;

    public ExtensionStatus(boolean skipExtensionsBefore, boolean skipDriver, boolean skipExtensionsAfter) {
        this.skipExtensionsBefore = skipExtensionsBefore;
        this.skipDriver = skipDriver;
        this.skipExtensionsAfter = skipExtensionsAfter;
    }

    public ExtensionStatus() {
        this(false, false, false);
    }

    public void or(ExtensionStatus extensionStatus) {

        if (extensionStatus == null) {
            return;
        }

        this.skipExtensionsBefore |= extensionStatus.skipExtensionsBefore;
        this.skipDriver |= extensionStatus.skipDriver;
        this.skipExtensionsAfter |= extensionStatus.skipExtensionsAfter;
    }

    public boolean skipExtensionsBefore() {
        return this.skipExtensionsBefore;
    }

    public boolean skipDriver() {
        return this.skipDriver;
    }

    public boolean skipExtensionsAfter() {
        return this.skipExtensionsAfter;
    }
}
