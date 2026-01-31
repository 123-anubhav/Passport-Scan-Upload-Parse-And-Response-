# scanuploadAndParse
basic demo of scanuploadAndParse of passport ,extra accoring to your requirement base anybody can add code , prvide somebasic info to test this repo

---
Septup at ec2 wright software website removed all thentry again
-
---
You now *finally* have the real reason your OCR‑B isn’t working on EC2:

### 🚨 The traineddata file you downloaded is **empty (0 bytes)**

```
-rw-r--r-- 1 root root   0 Jan 31 05:24 /usr/local/share/tessdata/ocrb.traineddata
```

That’s why Tesseract lists the language but **cannot load it** — an empty file cannot be used as OCR model. Tesseract will always fail like:

```
Error opening data file /usr/local/share/tessdata/ocrb.traineddata
Failed loading language 'ocrb'
```

That is expected because the file has *no content* — not because your environment or `TESSDATA_PREFIX` is wrong.

---

## ✅ Why the download failed

You tried this URL:

```
https://github.com/tesseract-ocr/tessdata_fast/raw/main/ocrb.traineddata
```

But that **resulted in 404 Not Found** because that repository **does NOT contain `ocrb.traineddata`**. Tesseract’s official language repos include many languages, but **OCR‑B is not included there by default**; it must be downloaded from *another source* (not in `tessdata`, `tessdata_fast`, or `tessdata_best`) unless you use a community or custom traineddata build. ([GitHub][1])

---

## ✅ The correct source for `ocrb.traineddata`

There is a community GitHub repo that **does include OCR‑B traineddata**:

🔗 [https://github.com/Shreeshrii/tessdata_ocrb](https://github.com/Shreeshrii/tessdata_ocrb)

This repo provides OCR‑B models trained for MRZ and similar use (both `ocrb.traineddata` and `ocrb_int.traineddata`). ([GitHub][2])

---

## ✅ Correct download steps

Run these exact commands **so the downloaded file is non‑zero and usable:**

```bash
# remove any bad file
sudo rm -f /usr/local/share/tessdata/ocrb.traineddata

# download the correct traineddata
sudo wget -O /usr/local/share/tessdata/ocrb.traineddata \
  https://github.com/Shreeshrii/tessdata_ocrb/raw/master/ocrb.traineddata
```

👉 After this, check the file size — it should NOT be 0 bytes:

```bash
ls -lh /usr/local/share/tessdata/ocrb.traineddata
```

---

## ✅ Set permissions & environment

```bash
sudo chmod 644 /usr/local/share/tessdata/ocrb.traineddata
sudo chown root:root /usr/local/share/tessdata/ocrb.traineddata

export TESSDATA_PREFIX=/usr/local/share/tessdata
echo 'export TESSDATA_PREFIX=/usr/local/share/tessdata' >> ~/.bashrc
source ~/.bashrc
```

---

## ✅ Verify Tesseract sees the language

```bash
/usr/local/bin/tesseract --list-langs
```

Output should include:

```
ocrb
```

---

## ✅ Run OCR

Now run:

```bash
/usr/local/bin/tesseract passport.jpeg stdout -l ocrb --psm 6
```

If the traineddata is correct, Tesseract should output OCR text instead of errors.

---

## 🧠 Why this works

🔹 Tesseract 5+ uses LSTM models (OCR engine) and must have **valid `.traineddata` files** for each language. ([Tesseract OCR][3])
🔹 The official repos (`tessdata`, `tessdata_fast`, `tessdata_best`) don’t include `ocrb.traineddata` by default, so the GitHub download failed. ([GitHub][1])
🔹 Using the community repo that *actually has OCR‑B models* fixes the “file empty” issue.

---


---
```yaml
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /usr/share/tesseract-ocr/5/tessdata

RUN curl -L \
 https://raw.githubusercontent.com/tesseract-ocr/tessdata/main/ocrb.traineddata \
 -o /usr/share/tesseract-ocr/5/tessdata/ocrb.traineddata

ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata

WORKDIR /data
CMD ["bash"]

```

---
---
