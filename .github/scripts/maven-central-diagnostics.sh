#!/usr/bin/env bash
#
# Prints what Maven Central answers right now for the files that Maven failed to resolve in this job, and what the
# local Maven repository holds for them. Meant for the failure path of a Maven build: it only prints and always exits
# with 0, so it never changes the result of the step that calls it.
#
# Maven records a failed lookup in a "<file>.lastUpdated" file next to where the file would be (its content says which
# repository was asked and what went wrong; an empty ".error" means "not found"). A restored Maven cache keeps the old
# modification times, so the files changed in the last 60 minutes are the lookups of this job.
#
# Usage, from any directory:
#     bash .github/scripts/maven-central-diagnostics.sh

repository="$HOME/.m2/repository"
central="https://repo.maven.apache.org/maven2"
max_files=10

echo "===== Maven Central diagnostics ($(date -u '+%Y-%m-%dT%H:%M:%SZ')) ====="

failed_lookups=$(find "$repository" -name '*.lastUpdated' -mmin -60 2>/dev/null | sort)
if [ -z "$failed_lookups" ]; then
  echo "No failed lookup in the last 60 minutes in $repository"
  echo "===== End of Maven Central diagnostics ====="
  exit 0
fi

echo "Failed lookups in the last 60 minutes: $(echo "$failed_lookups" | wc -l), showing up to $max_files"
echo "$failed_lookups" | head -n "$max_files" | while IFS= read -r last_updated; do
  path="${last_updated#"$repository"/}"
  path="${path%.lastUpdated}"
  echo
  echo "--- $path"
  echo "Content of $last_updated:"
  cat "$last_updated"
  echo "Content of $(dirname "$last_updated"):"
  ls -la "$(dirname "$last_updated")"
  echo "Maven Central answers for $central/$path:"
  curl -sS -o /dev/null -D - --max-time 20 -w 'HTTP %{http_code} from %{remote_ip} in %{time_total}s\n' "$central/$path"
done

echo "===== End of Maven Central diagnostics ====="
exit 0
