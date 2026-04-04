package com.orizon.openkiwi.core.code

/**
 * Embedded Python (Chaquopy) environment exposed to the LLM.
 * **Keep pip list in sync with** `app/build.gradle.kts` → `chaquopy { pip { install(...) } }`.
 */
object PythonEmbeddedEnv {
    const val PYTHON_VERSION = "3.13"

    /** Third-party packages installed via Chaquopy pip (comma-separated for prompts). */
    const val PIP_PACKAGES_LINE = "requests, beautifulsoup4"

    /**
     * Short hint for system prompts (one line).
     */
    fun oneLineHint(): String =
        "Embedded Python $PYTHON_VERSION (Chaquopy): full standard library; extra pip: $PIP_PACKAGES_LINE. " +
            "No numpy/scipy/pandas unless added to Gradle."

    /**
     * Full paragraph for tool descriptions.
     */
    fun toolDescriptionSuffix(): String =
        "Local Python is Chaquopy $PYTHON_VERSION. " +
            "Standard library is available (e.g. itertools, math, functools, collections, datetime, json, re, " +
            "pathlib, decimal, statistics, random, hashlib, typing, enum, contextlib). " +
            "Extra pip packages: $PIP_PACKAGES_LINE. " +
            "Do not assume numpy, scipy, or pandas unless the project adds them to build.gradle.kts."
}
