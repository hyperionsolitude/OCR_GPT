# Contributing to OCR GPT

Thank you for your interest in contributing to OCR GPT! This document provides guidelines and information for contributors.

## Code of Conduct

By participating in this project, you agree to abide by our Code of Conduct. Please be respectful and constructive in all interactions.

## Getting Started

### Prerequisites

- Android Studio (latest version)
- Android SDK (API level 21 or higher)
- Git
- JDK 17

### Development Setup

1. Fork the repository
2. Clone your fork:
   ```bash
   git clone https://github.com/your-username/OCR_GPT.git
   cd OCR_GPT
   ```
3. Set up Android SDK path in `local.properties`:
   ```properties
   sdk.dir=/path/to/your/Android/Sdk
   ```
4. Open the project in Android Studio
5. Sync Gradle files
6. Run the app on a device or emulator

## Development Guidelines

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions small and focused
- Use proper indentation (match existing project style)

### Code Quality

- All code must pass ktlint checks
- All code must pass detekt static analysis
- Run Android Lint and fix issues where feasible
- Write unit tests for new functionality
- Ensure all checks pass locally before submitting

### Commit Messages

Use clear, descriptive commit messages:

```
feat: add image cropping functionality
fix: resolve memory leak in OCR processing
docs: update README with new features
style: format code according to ktlint rules
refactor: extract common OCR logic to utility class
```

## Pull Request Process

1. Create a feature branch from `android`:
   ```bash
   git checkout -b feat/your-feature-name
   ```

2. Make your changes following the guidelines above

3. Run tests and quality checks:
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew ktlintCheck
   ./gradlew detekt
   ./gradlew lintDebug
   ```

4. Commit your changes with descriptive messages

5. Push to your fork:
   ```bash
   git push origin feat/your-feature-name
   ```

6. Create a Pull Request with:
   - Clear description of changes
   - Reference to any related issues
   - Screenshots if UI changes
   - Test and lint results

## Testing

### Unit Tests
- Write unit tests for new functionality
- Aim for good test coverage
- Use descriptive test names
- Test both success and failure cases

### UI Tests
- Add UI tests for new screens or significant UI changes
- Test on different screen sizes when applicable

### Manual Testing
- Test on different Android versions (API 21+)
- Test on different device sizes
- Test OCR functionality with various image types
- Test AI integration with different prompts

## Issue Reporting

When reporting issues, please include:

- Clear description of the problem
- Steps to reproduce
- Expected vs actual behavior
- Device information (Android version, device model)
- App version
- Screenshots or logs if applicable

## Feature Requests

When suggesting new features:

- Check existing issues first
- Provide clear use case and benefits
- Consider implementation complexity
- Think about backward compatibility

## Release Process

Releases are managed through GitHub Actions:

1. Version bump in `app/build.gradle`
2. Update `CHANGELOG.md`
3. Create and push a tag: `git tag vX.Y.Z`
4. Push tag: `git push origin vX.Y.Z`
5. The release workflow will build artifacts

## Documentation

- Update README.md for significant changes
- Add inline documentation for complex code
- Update API documentation if applicable
- Keep CHANGELOG.md updated

## Questions?

If you have questions about contributing, please:

- Open a GitHub issue with the "question" label
- Check existing issues and discussions
- Review the codebase and documentation

Thank you for contributing to OCR GPT! 🚀
