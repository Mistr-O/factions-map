const express = require("express");
const cors = require("cors");

const app = express();
app.use(cors());

app.get("/players", async (req, res) => {
  try {
    const response = await fetch("https://api.wolver002.com/map/players");
    const data = await response.json();
    res.json(data);
  } catch (err) {
    res.status(500).json({ error: "API failed", details: err.message });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log("Server running on " + PORT));
