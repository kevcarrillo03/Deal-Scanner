# Deal Scanner

A barcode price-comparison app. Scan a product's barcode with your phone, and Deal Scanner identifies the product and shows what it costs at stores across the US: major retailers like Walmart, Target, and Walgreens first, then every other store selling it, cheapest first.

**Live demo:** [deal-scanner.expo.app](https://deal-scanner.expo.app). Open it on a phone, allow the camera, and scan a grocery item, or tap **Type barcode** and enter `028400183826`. The backend runs on a free host that sleeps when idle, so the first scan after a quiet period can take a minute or more while it wakes up.

The project has two parts:

- **A React Native (Expo) app** that runs on iOS and Android through Expo Go, and in any modern browser as a website.
- **A Spring Boot REST API** that identifies the product, gathers prices from several data sources, caches results in memory and in PostgreSQL, and keeps scan and price history.

## Features

- **Barcode scanning** of UPC-A, UPC-E, EAN-13, and EAN-8 barcodes with the camera, with an on-screen guide box. Barcodes can also be typed in, which helps when a camera can't focus or isn't available.
- **Works as a website.** The same code runs in mobile browsers, including iPhone Safari and Chrome. Browsers without a built-in barcode detector decode barcodes with ZXing compiled to WebAssembly.
- **Product lookup with a fallback.** Barcodes are identified through UPCItemDB, then Open Food Facts when UPCItemDB is rate-limited, unavailable, or doesn't know the product, then the product saved from an earlier scan.
- **Prices from every store, with major retailers first.**
  - Prices come from Google Shopping (through SerpApi) and from store prices recorded by UPCItemDB (anything older than 90 days is dropped).
  - The results screen has two sections:
    - **Major retailers:** Walmart, Target, Best Buy, Amazon, Costco, Sam's Club, Kroger, Walgreens, and CVS.
    - **More stores:** every other store, so shoppers can scroll through all available prices.
  - Each listing shows its title and picture, so different sizes and flavors are easy to spot.
- **Clean results.** Store names are normalized ("Wal-Mart.com" and "Walmart - Seller" both become "Walmart", and eBay sellers are grouped as "eBay"), one listing is kept per store, and results are sorted cheapest first. Only major retailers can be marked "Best price".
- **Caching** of results for 12 hours, in memory and in PostgreSQL, so repeat scans return in milliseconds, survive restarts, and don't use API quota.
- **Scan and price history.** Each device has a Recent Scans screen, and every price check is saved over time. The results screen shows the lowest price seen. Best and lowest prices count major retailers only, so a single-serve bag at a corner store doesn't look like the best deal.
- **Clear error and loading states.**
  - Separate messages for unknown barcodes, rate limits, and network problems.
  - A "Prices as of" time, so cached prices are never mistaken for live ones.
  - A "Waking up the server" message while the free host starts.

## Tech Stack

| Part | Technologies |
|---|---|
| App | React Native 0.86, Expo SDK 57, Expo Router, expo-camera, React Native Web, TypeScript |
| Backend | Java 17, Spring Boot 4, Spring WebClient (non-blocking HTTP), Project Reactor, Caffeine cache, Spring Boot Actuator |
| Database | PostgreSQL 18, Spring Data JPA, Flyway migrations, Docker Compose for local development |
| Hosting | Backend on [Render](https://render.com) (Docker), database on [Neon](https://neon.tech), website on [EAS Hosting](https://docs.expo.dev/eas/hosting/introduction/) |
| External APIs | [UPCItemDB](https://www.upcitemdb.com/) and [Open Food Facts](https://world.openfoodfacts.org/) (product lookup), [SerpApi](https://serpapi.com/) Google Shopping (prices) |

## Architecture

```mermaid
flowchart LR
    Phone["Expo Go app<br/>(iOS / Android)"] --> API
    Browser["Website<br/>(EAS Hosting)"] --> API
    API["Spring Boot API<br/>(Render, Docker)"] --> DB[("PostgreSQL<br/>(Neon)")]
    API --> UPC["UPCItemDB"]
    API --> OFF["Open Food Facts"]
    API --> Serp["SerpApi<br/>(Google Shopping)"]
```

### What happens when a barcode is scanned

```mermaid
sequenceDiagram
    participant App as App / Website
    participant API as Spring Boot API
    participant Cache as Caffeine Cache
    participant DB as PostgreSQL
    participant UPC as UPCItemDB
    participant OFF as Open Food Facts
    participant Serp as SerpApi (Google Shopping)

    App->>API: GET /api/v1/scan/{upc}
    API->>Cache: Look up UPC
    alt In the cache
        Cache-->>API: Stored result
    else Not in the cache
        API->>DB: Latest price check
        alt Checked within 12 hours
            DB-->>API: Saved product and prices
        else No recent check
            API->>UPC: Look up barcode
            alt Found
                UPC-->>API: Product name + recorded store prices
            else Rate-limited, unavailable, or not found
                API->>OFF: Look up barcode
                OFF-->>API: Brand, product name, size, image
            end
            API->>Serp: Search Google Shopping for the product name
            Serp-->>API: Shopping listings
            API->>API: Normalize stores, mark major retailers,<br/>keep cheapest per store, sort
            API->>DB: Save product and price check
        end
    end
    API->>DB: Record the scan for this device
    API-->>App: JSON: product + prices
```

1. The app scans (or the user types) a barcode and opens the results screen, which calls the backend.
2. The backend checks its in-memory cache, then the database. Prices checked within the last 12 hours are returned straight away, with no external API calls.
3. Otherwise it identifies the product, trying UPCItemDB, then Open Food Facts, then the product saved from an earlier scan.
4. It searches Google Shopping for the product name and merges those listings with UPCItemDB's recorded store prices. Store names are normalized, major retailers are marked, and the cheapest listing per store is kept.
5. The product and price check are saved to PostgreSQL, the scan is recorded for the device, and the app shows **Major retailers** and **More stores**, each with a link to the listing.

## Design Decisions

- **Two-level cache.**
  - **Caffeine `AsyncCache` in memory:** if two scans of the same barcode arrive at the same time, they share one lookup instead of making duplicate API calls. Each entry expires exactly 12 hours after its prices were fetched, and failed lookups are never cached.
  - **PostgreSQL:** fresh results survive restarts and deploys.
- **A fallback for product lookup.** UPCItemDB's free trial limits requests per IP address, and hosting platforms like Render share outgoing IP addresses between many apps, so the shared address is often already over the limit. Open Food Facts (free, no key, strong coverage of groceries) and the saved product keep scanning working when UPCItemDB is unavailable.
- **Show every store, but trust major retailers.** Google Shopping returns around 40 listings per product, mostly from smaller shops, and many are a different size or flavor. Hiding them left too few prices, so every store is shown. Major retailers are listed first and are the only ones that can be "Best price" or count toward price history.
- **Recording why a search found nothing.** Each price search is saved as a `price_check` row, even when it finds no prices, so the backend can tell "checked an hour ago, nothing found" apart from "never checked" and doesn't repeat searches.
- **Mixing blocking and non-blocking code safely.** External APIs are called with the non-blocking `WebClient`. Database calls (JPA, which blocks) run on Reactor's `boundedElastic` thread pool so they never stall the non-blocking threads.
- **Degrading gracefully.** If Google Shopping fails, the app still gets the product and UPCItemDB's prices. If the database is unreachable, scans still work without being saved. If price history fails to load, the results screen leaves it out instead of showing an error.
- **Versioned schema changes.** The database schema is managed with Flyway migrations (`V1__create_tables.sql`, `V2__add_major_retailer.sql`). Hibernate only validates that entities match the tables.
- **Keeping secrets out of the code.** API keys and database credentials come from environment variables or git-ignored `.env` files. Committed files only refer to them by name.

## API

### `GET /api/v1/scan/{upc}`

Identifies a barcode and returns the product with its prices, cheapest first. `major_retailer` tells the app which section each price belongs to.

An optional `X-Device-Id` header records the scan for that device's Recent Scans list.

**Example response** (`200 OK`):

```json
{
  "upc": "028400183826",
  "product_name": "Lay's Baked Original Potato Crisps 6.25 Ounce Plastic Bag",
  "prices": [
    {
      "store": "Nadys Liquor",
      "price": 3.88,
      "title": "Lay's Oven Baked Original",
      "link": "https://...",
      "thumbnail": "https://...",
      "major_retailer": false
    },
    {
      "store": "Target",
      "price": 4.19,
      "title": "Lay's Oven Baked Original Potato Chips - 6.25oz",
      "link": "https://...",
      "thumbnail": "https://...",
      "major_retailer": true
    }
  ],
  "fetched_at": "2026-09-30T23:30:12.418204Z"
}
```

**Errors:**

| Status | Meaning |
|---|---|
| `404 Not Found` | No source (UPCItemDB, Open Food Facts, or the database) knows this barcode |
| `429 Too Many Requests` | UPCItemDB's rate limit was reached and no other source could identify the product |
| `502 Bad Gateway` | UPCItemDB failed and no other source could identify the product |

### `GET /api/v1/scans/recent`

The products a device scanned most recently (up to 20), each listed once with the cheapest major-retailer price from its latest price check. Requires the `X-Device-Id` header (`400` if missing).

```json
[
  {
    "upc": "028400183826",
    "product_name": "Lay's Baked Original Potato Crisps 6.25 Ounce Plastic Bag",
    "image_url": "https://...",
    "last_scanned_at": "2026-09-30T23:30:12.505127Z",
    "best_price": { "store": "Target", "price": 4.19 }
  }
]
```

### `GET /api/v1/products/{upc}/history?days=30`

A product's price checks over the last `days` days (1 to 365, default 30), newest first, with major-retailer prices and the lowest one seen. Returns `404` if the product has never been scanned.

```json
{
  "upc": "028400183826",
  "product_name": "Lay's Baked Original Potato Crisps 6.25 Ounce Plastic Bag",
  "days": 30,
  "lowest_price": { "store": "Target", "price": 4.19, "checked_at": "2026-09-30T23:30:12.418204Z" },
  "checks": [
    {
      "checked_at": "2026-09-30T23:30:12.418204Z",
      "prices": [
        { "store": "Target", "price": 4.19 },
        { "store": "Walgreens", "price": 4.99 }
      ]
    }
  ]
}
```

### `GET /actuator/health`

Returns `{"status":"UP"}` when the API is running and connected to the database. Used by the hosting platform's health check.

## Running Locally

### Prerequisites

- Java 17 or newer
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (runs the PostgreSQL database)
- Node.js (LTS)
- The [Expo Go](https://expo.dev/go) app on your phone, to run the app on a phone
- A free [SerpApi](https://serpapi.com/) API key (UPCItemDB's trial endpoint and Open Food Facts need no key)

### 1. Run the backend

Create `backend/.env` with your SerpApi key (no quotes, no spaces):

```
SERPAPI_API_KEY=your_serpapi_key
```

Then, with Docker running:

```bash
cd backend
./mvnw spring-boot:run
```

This also starts PostgreSQL from `compose.yaml` on port `5433` and applies the Flyway migrations. The API runs on port `8080`. Without a key, the backend still works but only returns UPCItemDB's prices.

### 2. Run the app

Create `frontend/.env.local` with the backend's address:

```
EXPO_PUBLIC_API_URL=http://YOUR_COMPUTER_IP:8080
```

Find your computer's local IP address with `ipconfig getifaddr en0` on macOS. To use the deployed backend instead, set it to its `https://` address.

```bash
cd frontend
npm install
npx expo start
```

- **On a phone:** scan the QR code shown in the terminal with your phone's camera (iOS) or the Expo Go app (Android). With a local backend, the phone must be on the same Wi-Fi network as your computer.
- **In a browser:** run `npx expo start --web` instead and open `http://localhost:8081`. Browsers only allow camera access on `https://` pages or `localhost`, so use your computer's webcam or **Type barcode**.

## Deploying (free)

### 1. Database on Neon

1. Create a free Neon project. Turn off connection pooling and copy the connection string. It looks like `postgresql://USER:PASSWORD@HOST/DATABASE?sslmode=require`.
2. Split it into the three values Spring needs:
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://HOST/DATABASE?sslmode=require`
   - `SPRING_DATASOURCE_USERNAME` = `USER`
   - `SPRING_DATASOURCE_PASSWORD` = `PASSWORD`

Flyway creates and updates the tables automatically when the backend starts.

### 2. Backend on Render

1. Push this repo to GitHub.
2. In Render, choose **New → Blueprint** and select the repo. Render reads [`render.yaml`](render.yaml) and builds `backend/Dockerfile`.
3. Enter the environment variables it asks for: `SERPAPI_API_KEY` and the three `SPRING_DATASOURCE_*` values.
4. When the deploy finishes, `https://YOUR-SERVICE.onrender.com/actuator/health` should show `{"status":"UP"}`.

Render redeploys automatically whenever files in `backend/` change on GitHub. Free Render services sleep after about 15 minutes without traffic, so the first request after that is slow while the server wakes up.

### 3. Website on EAS Hosting

1. Create a free [Expo](https://expo.dev) account and log in:
   ```bash
   cd frontend
   npx eas-cli login
   ```
2. Set your backend's address in the `deploy:web` script in `frontend/package.json` (it currently points to `https://deal-scanner-backend.onrender.com`).
3. Build and publish:
   ```bash
   npm run deploy:web
   ```

The first deploy asks you to link an Expo project and choose a subdomain, then prints the site's address. To publish a preview without replacing the live site, run the export and then `npx eas-cli deploy` without `--prod`.

### Running the Docker image locally

```bash
cd backend
docker build -t deal-scanner-backend .
docker run -p 8080:8080 \
  -e SERPAPI_API_KEY=your_key \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/dealscanner \
  -e SPRING_DATASOURCE_USERNAME=dealscanner \
  -e SPRING_DATASOURCE_PASSWORD=dealscanner \
  deal-scanner-backend
```

This uses the database from `compose.yaml` (`docker compose up -d`).

## Project Structure

```
Deal-Scanner/
├── render.yaml                                   Render deployment blueprint
├── backend/                                      Spring Boot REST API
│   ├── Dockerfile                                Production container image
│   ├── compose.yaml                              Local PostgreSQL database
│   └── src/
│       ├── main/resources/db/migration/          Flyway SQL migrations (V1, V2)
│       └── main/java/com/kevincarrillo/dealscanner/
│           ├── ScanController.java               /api/v1/scan endpoint
│           ├── ScanService.java                  Scan flow: cache, database, product lookup, prices
│           ├── UpcItemDbClient.java              Product lookup on UPCItemDB
│           ├── OpenFoodFactsClient.java          Fallback product lookup on Open Food Facts
│           ├── PriceService.java                 Google Shopping search, store normalization, merging
│           ├── ScanStore.java                    Saving and loading scans in the database
│           ├── HistoryController.java            Recent scans and price history endpoints
│           ├── HistoryService.java               Builds the history responses
│           ├── db/                               JPA entities and repositories
│           └── *Response.java, ScanResult.java, StorePrice.java   JSON models
└── frontend/                                     React Native (Expo) app and website
    └── src/
        ├── app/
        │   ├── _layout.tsx                       Navigation stack
        │   ├── index.tsx                         Camera scanner with type-in option
        │   ├── history.tsx                       Recent scans screen
        │   └── results/[upc].tsx                 Prices by section, and price history
        ├── components/BarcodeEntry.tsx           Type-in barcode form
        ├── api/
        │   ├── scan.ts                           Backend client and shared types
        │   └── deviceId.ts                       Random per-device ID (SecureStore, or localStorage on the web)
        ├── hooks/useIsSlow.ts                    Detects slow loads (server waking up)
        └── utils/format.ts                       Price and date formatting
```

## Limitations

- **US only.** Product coverage, retailers, and prices (USD) are all US-focused.
- **Listings can be a different product.** Google Shopping returns similar items, not only exact matches, so a listing can be a different flavor, size, or multipack. The **More stores** section warns about this, and each listing shows its title and picture, but a mismatched listing can occasionally appear under a major retailer too.
- **Free-tier API limits.** UPCItemDB's trial allows about 100 lookups per day per IP address, and SerpApi's free plan allows 250 searches per month. Caching reduces how quickly these are used.
- **Non-food products on shared hosting.** When UPCItemDB is rate-limited (common on Render, where the IP address is shared), products are identified through Open Food Facts, which covers food and drinks. Other products can't be identified until UPCItemDB is available, and prices then come from Google Shopping only.
- **Browser scanning is slower than native.** On browsers without a built-in barcode detector, such as iPhone Safari, barcodes are decoded with ZXing in WebAssembly, which can take a moment to lock on. Typing the barcode is always available.
- **Cold starts on the free host.** The deployed backend sleeps when idle, so the first request after a quiet period can take a minute or more.
