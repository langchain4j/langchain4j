FROM --platform=$BUILDPLATFORM ollama/ollama:latest
RUN /bin/sh -c "/bin/ollama serve & sleep 1 && ollama pull phi"
ENTRYPOINT ["/bin/ollama"]
CMD ["serve"]