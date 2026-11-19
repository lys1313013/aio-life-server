"""从两份官方榜单生成新的 Flyway 数据迁移。依赖 beautifulsoup4。"""
import argparse
import re
from datetime import date
from pathlib import Path
from urllib.request import urlopen

from bs4 import BeautifulSoup


def snapshot(kind, pages):
    ids = set()
    rows = []
    for offset in range(0, 250, 25):
        url = f"https://{kind}.douban.com/top250?start={offset}"
        if pages:
            html = (pages / f"{kind}-{offset}.html").read_bytes()
        else:
            with urlopen(url, timeout=20) as response:
                html = response.read()
        doc = BeautifulSoup(html, "html.parser", from_encoding="utf-8")
        items = doc.select(".item")
        page = doc.select_one(".paginator .thispage")
        if len(items) != 25 or not page or page.get_text(strip=True) != str(offset // 25 + 1):
            raise ValueError(f"榜单分页不完整 {url}")
        for rank, item in enumerate(items, offset + 1):
            link = item.select_one(".hd a" if kind == "movie" else ".pl2 a")
            match = re.fullmatch(rf"https://{kind}\.douban\.com/subject/([0-9]{{1,12}})/", link["href"])
            if not match or match[1] in ids:
                raise ValueError(f"无效或重复的条目 {url} #{rank}")
            if kind == "movie" and item.select_one(".pic em").text != str(rank):
                raise ValueError(f"电影名次不连续 {rank}")
            title = (link.select_one(".title").get_text(strip=True) if kind == "movie" else link["title"]).strip()
            if not title or len(title) > 255:
                raise ValueError(f"无效标题 {rank}")
            cover = item.select_one("img")["src"]
            if not re.fullmatch(r"https://img[0-9]*\.doubanio\.com/view/[a-zA-Z0-9_./-]+", cover):
                raise ValueError(f"未知封面来源 {rank}")
            ids.add(match[1])
            rows.append((kind, rank, match[1], title, cover))
    return rows


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path, help="尚不存在的新版本迁移文件")
    parser.add_argument("--pages", type=Path, help="已下载的官方 HTML 分页目录")
    parser.add_argument("--as-of", type=date.fromisoformat, default=date.today())
    args = parser.parse_args()
    rows = snapshot("movie", args.pages) + snapshot("book", args.pages)
    values = ",\n".join("  (" + ", ".join(map(quote, row)) + ")" for row in rows)
    template = Path(__file__).with_name("douban_rankings.sql.in").read_text()
    with args.output.open("x", encoding="utf-8") as out:
        out.write(template.replace("@DATE@", args.as_of.isoformat()).replace("@ROWS@", values))


if __name__ == "__main__":
    main()
