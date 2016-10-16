#!/bin/bash
echo "creating long output..."

for i in `seq 3000`;
do
  OUTPUT="$OUTPUT\n$i) here is a very long line. Bacon ipsum dolor amet shankle kevin ball tip tenderloin burgdoggen jerky rump frankfurter"
done

echo -e $OUTPUT
