FROM golang:1.24-bookworm AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/spur-items .

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/spur-items /usr/local/bin/spur-items
ENV SPUR_DATA_DIR=/data
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/spur-items"]
