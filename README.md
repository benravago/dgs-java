# dgs-java
Netflix DGS for mere mortals

This is a port of Netflix's [DGS Framework](https://github.com/Netflix/dgs-framework) for GraphQL-java from Kotlin to Java.

At this point, the port is not guaranteed to work as my main objective
was to try to understand how some of the framework's features are implemented
and it seemed to me that re-writing the code to java would be a good way to get at that.

The code, at least, compiles and I may eventually get the DGS example running
but that will probably mean converting the [dgs-codegen](https://github.com/Netflix/dgs-codegen) feature (and unit tests), as well.
