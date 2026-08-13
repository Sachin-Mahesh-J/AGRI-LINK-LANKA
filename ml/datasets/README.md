# PlantVillage dataset (without augmentation)

## Sri Lanka crop selection

You can keep the **full** PlantVillage download on disk (apple, grape, etc.). Training scripts **automatically ignore** classes that are not grown in Sri Lanka.

### Used for image ML (PlantVillage has these)

| Crop | PlantVillage prefix | Notes |
|------|---------------------|-------|
| **Maize / Corn** | `Corn_(maize)___` | Common in SL |
| **Tomato** | `Tomato___` | Common in SL |
| **Strawberry** | `Strawberry___` | Hill country / seasonal |
| **Potato** | `Potato___` | Up-country / seasonal in SL |
| **Bell pepper** | `Pepper___` | PlantVillage folder name for pepper |

### Not used (excluded automatically)

Apple, blueberry, cherry, grape, orange, peach, raspberry, soybean, squash, etc.

### Not in PlantVillage at all

| Crop | How AgriScout handles it |
|------|--------------------------|
| **Rice / Paddy** | Symptom text classifier + crop calendar (main SL crop) |
| **Wheat** | Crop calendar + rules |

You do **not** need to delete apple/grape folders manually. Optional: run `build_sl_subset.py` to copy only SL classes into a smaller folder for faster training.

## Where to extract

```text
ml/datasets/PlantVillage/
  Corn_(maize)___Common_rust/
  Tomato___Late_blight/
  Apple___Black_rot/          ← kept on disk, ignored by training
  ...
```

## Commands

```powershell
cd MAD\prep\AgriScout\ml
.venv\Scripts\activate
python scripts/prepare_dataset.py      # verify + show SL vs ignored classes
python scripts/build_sl_subset.py      # optional: smaller SL-only copy
python scripts/train_image_classifier.py
```
