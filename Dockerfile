FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 SHOP_DB_PATH=/data/shop.db
WORKDIR /app
COPY . /app
RUN useradd --create-home shop && mkdir -p /data && chown shop:shop /data
USER shop
VOLUME ["/data"]
EXPOSE 8000
CMD ["python", "start.py", "--host", "0.0.0.0", "--port", "8000"]
