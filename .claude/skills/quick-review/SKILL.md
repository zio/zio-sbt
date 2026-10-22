---
name: quick-review
description: Use when you want a fast, structured code review of recent changes, or when a developer asks to "review my code" or "check my changes"
---

# Quick Code Review

Perform a fast, comprehensive code review on recently changed files.

## When to Use
- Developer asks for a code review
- After completing a feature or bug fix
- Before creating a pull request

## Steps

1. **Get the changed files:**
```bash
git diff --name-only HEAD~1
```
If on a feature branch:
```bash
git diff --name-only main...HEAD
```

2. **Read each changed file** to understand the full context, not just the diff.

3. **Review the diff** for issues:
```bash
git diff HEAD~1
```

4. **Provide structured feedback:**

**Summary:** One-sentence overall assessment.

**Issues Found:**
- Reference each issue with `file:line`
- Explain why it's a problem
- Provide the fix

**Suggestions:**
- Optional improvements (not bugs)
- Performance or readability enhancements

**Verdict:** APPROVE, REQUEST_CHANGES, or NEEDS_DISCUSSION

## Rules
- Be specific and actionable. No vague comments like "could be better."
- Reference exact file paths and line numbers.
- Distinguish between bugs (must fix) and suggestions (nice to have).
- If the code is good, say so. Don't invent issues.
