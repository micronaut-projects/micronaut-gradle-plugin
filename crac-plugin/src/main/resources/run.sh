#!/bin/bash

set -- \
        "-XX:CRaCRestoreFrom=cr" \
        "$@"
eval "set -- $(
        printf '%s\n' "$JAVA_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'
exec /azul-crac-jdk/bin/java "$@"
