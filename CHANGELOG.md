# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.1] - 2026-05-10

### Fixed
- `ClassScanner` now catches `LinkageError` (and its subclasses) when loading classes,
  skipping unresolvable types instead of aborting the scan.
  Relevant for Spring Boot and similar projects whose compile-time classpath includes
  framework classes not available in the standalone-JAR environment.

### Changed
- README expanded with Spring Boot classpath note and standalone-JAR guidance.
- IntelliJ plugin sub-module removed; scanner is distributed as a plain JAR only.

## [1.0.0] - 2026-05-10

### Added
- `Main` — introductory demo: `ClassLayout`, `GraphLayout`, linked-`Node` chain.
- `MemoryAnalyser` — data-structure comparison (primitive arrays, boxed collections,
  `ArrayList`, `LinkedList`, `HashMap`) with field-reordering and scaling demos.
- `MemoryReport` — structured tabular report with key findings and 1 M-element projections.
- `ProjectScanner` — CLI that walks compiled `.class` files, analyses each with JOL, and
  writes two Excel workbooks (`memory-report.xlsx` + `memory-report-developer.xlsx`).
- `LayoutAnalyser` / `ClassScanner` / `LayoutAnalysis` — reusable scanner API.
- `ExcelReporter` / `DevFriendlyReporter` — technical and developer-friendly workbooks.
- `BadOrder` / `GoodOrder` / `Node` — demonstration domain classes.
- `MemoryReportTest` — parameterised JUnit 5 assertions on memory-layout guarantees.
- Published to Maven Central under `io.github.mm-asraf:jol-memory-analyser`.
- Apache License 2.0.

[Unreleased]: https://github.com/mm-asraf/jol-memory-analyser/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/mm-asraf/jol-memory-analyser/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/mm-asraf/jol-memory-analyser/releases/tag/v1.0.0
