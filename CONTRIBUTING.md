# Contributing to JOL Memory Analyser

Thank you for taking the time to improve this project. This document explains how to build, test, and submit changes.

## Ground rules

- Be respectful and follow the [Code of Conduct](CODE_OF_CONDUCT.md).
- For **security-sensitive** issues, do not open a public issue; see [SECURITY.md](SECURITY.md).

## What you need

- **JDK 17+**
- **Apache Maven 3.6+**
- A **Git** client and a **GitHub** account (fork + pull request).

## Get the code

```bash
git clone https://github.com/mm-asraf/jol-memory-analyser.git
cd jol-memory-analyser
```

## Build and test

```bash
mvn -q verify
```

This compiles, runs unit tests, and runs the Javadoc build. Fix any failures before opening a PR.

Useful variants:

```bash
mvn -q test              # tests only
mvn -q package           # also builds jars (including standalone uber-JAR)
```

## Pull requests

1. **Fork** the repository and create a **branch** from `master` (or `main`, if that becomes the default).
2. Make **focused** changes (one concern per PR where possible).
3. Ensure **`mvn verify`** passes locally.
4. Open a **pull request** with a clear description: what changed, why, and how to exercise it.
5. Link **related issues** with `Fixes #123` in the body if applicable.

## Publishing to Maven Central (maintainers only)

Releases are published by project maintainers with GPG signing and Sonatype credentials. If you are not a maintainer, you do not need to run `mvn deploy -Prelease`. Regular contributors only need `mvn verify`.

## Questions

Open a [GitHub Discussion](https://github.com/mm-asraf/jol-memory-analyser/discussions) or an [issue](https://github.com/mm-asraf/jol-memory-analyser/issues) for design questions or support.
