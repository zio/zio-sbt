# Agent Instructions

**Never commit to `main`. Checkout feature/bugfix branch first.**

After doc generation, run:
```bash
sbt scalafmtAll
sbt "++2.13; check"
```
