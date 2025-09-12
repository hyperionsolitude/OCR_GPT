# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- MIT License
- Comprehensive GitHub Actions CI/CD workflows
- Code quality checks (ktlint, detekt)
- Security scanning with CodeQL
- Automated release workflow
- Issue and PR templates
- Contributing guidelines
- Security policy
- EditorConfig for consistent code formatting

### Changed
- Upgraded Gradle wrapper to 8.9 and Android Gradle Plugin to 8.7.0
- Updated Kotlin to 1.9.24 and target/compile SDK to 35
- Merged CI workflows into a single `android-ci.yml` (tests, quality, builds, CodeQL)
- Updated README and QUICK_START for JDK 17 and accurate features
- Refactored code to satisfy ktlint and detekt

## [1.0.0] - 2025-01-27

### Added
- Initial release with basic OCR and AI integration
- Conversation context and model selection
- Image cropping and copy functionality
- Accessibility features and performance optimizations
- Modern UI with WebView for rich text display
- Multiple API key management
- Dynamic model fetching from Groq API
