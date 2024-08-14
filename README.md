# GitWatch4J
GitWatch4J is a [GitWatch](https://github.com/gitwatch/gitwatch) remake written in Java.

The core loop of the program works the same as [GitWatch](https://github.com/gitwatch/gitwatch).
+ Watch the directory provided
+ On any new files:
    + **wait 2 seconds...**
    + `git add [file]`
    + `git commit -m "Cleanup"`

To make editing easier for programs that auto-save, I've included a `-delay` parameter that waits until the file has last been modified at least 10 minutes ago before commit.

# Requirements
+ Java 8 **or greater**