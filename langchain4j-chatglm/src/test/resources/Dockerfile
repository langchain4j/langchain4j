FROM python:3.8-slim
WORKDIR /app
COPY ./api.py /app
RUN pip install --no-cache-dir fastapi uvicorn transformers torch
EXPOSE 8000
CMD ["python", "api.py"]