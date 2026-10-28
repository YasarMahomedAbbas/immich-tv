/**
 * The only layer that talks to anything outside the process: HTTP, image loading headers and
 * stored settings. It implements the domain repositories and maps every server response into
 * domain models before it leaves. Only `AppContainer` names a class here; `ArchitectureTest`
 * enforces it.
 */
package io.github.yasarmahomedabbas.immichtv.data
