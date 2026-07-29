import { createHash } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const knowledgeRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const rawRoot = path.join(knowledgeRoot, "raw");

const sourceSets = [
    {
        id: "spring-security-6.5-migration",
        rootUrl: "https://docs.spring.io/spring-security/reference/6.5/migration/",
        entries: ["index.html"],
    },
    {
        id: "jdk-11-migration",
        rootUrl: "https://docs.oracle.com/en/java/javase/11/migrate/",
        entries: ["index.html"],
    },
    {
        id: "jdk-17-migration",
        rootUrl: "https://docs.oracle.com/en/java/javase/17/migrate/",
        entries: ["index.html"],
    },
    {
        id: "jdk-21-migration",
        rootUrl: "https://docs.oracle.com/en/java/javase/21/migrate/",
        entries: ["index.html"],
    },
];

function discoverHtmlLinks(html, currentUrl, rootUrl) {
    const links = [];
    const pattern = /href\s*=\s*["']([^"'#]+)["']/gi;
    for (const match of html.matchAll(pattern)) {
        const candidate = new URL(match[1], currentUrl);
        candidate.hash = "";
        if (candidate.origin !== rootUrl.origin) continue;
        if (!candidate.pathname.startsWith(rootUrl.pathname)) continue;
        if (!candidate.pathname.endsWith(".html")) continue;
        links.push(candidate.href);
    }
    return links;
}

async function crawl(source) {
    const rootUrl = new URL(source.rootUrl);
    const outputRoot = path.join(rawRoot, source.id);
    const queue = source.entries.map((entry) => new URL(entry, rootUrl).href);
    const visited = new Set();
    const records = [];

    while (queue.length > 0) {
        const url = queue.shift();
        if (visited.has(url)) continue;
        visited.add(url);

        const response = await fetch(url, { redirect: "follow" });
        if (!response.ok) throw new Error(`${response.status} ${url}`);
        const contentType = response.headers.get("content-type") ?? "";
        if (!contentType.toLowerCase().includes("text/html")) {
            throw new Error(`Expected HTML but received ${contentType}: ${url}`);
        }

        const html = await response.text();
        const relativePath = decodeURIComponent(new URL(url).pathname.slice(rootUrl.pathname.length));
        const outputPath = path.resolve(outputRoot, relativePath || "index.html");
        if (!outputPath.startsWith(path.resolve(outputRoot) + path.sep)) {
            throw new Error(`Unsafe output path for ${url}`);
        }
        await mkdir(path.dirname(outputPath), { recursive: true });
        await writeFile(outputPath, html, "utf8");

        records.push({
            sourceId: source.id,
            url,
            file: path.relative(knowledgeRoot, outputPath).replaceAll("\\", "/"),
            bytes: Buffer.byteLength(html),
            sha256: createHash("sha256").update(html).digest("hex"),
        });

        for (const link of discoverHtmlLinks(html, url, rootUrl)) {
            if (!visited.has(link)) queue.push(link);
        }
    }

    return records;
}

const records = [];
for (const source of sourceSets) {
    const sourceRecords = await crawl(source);
    records.push(...sourceRecords);
    console.log(`${source.id}: ${sourceRecords.length} files`);
}

await mkdir(rawRoot, { recursive: true });
await writeFile(
    path.join(rawRoot, "web-download-manifest.json"),
    `${JSON.stringify({ retrievedAt: new Date().toISOString(), records }, null, 2)}\n`,
    "utf8",
);
