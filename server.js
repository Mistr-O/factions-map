const express = require("express");
const cors = require("cors");

const app = express();

app.use(cors());

// Proxy endpoint
app.get("/players", async (req, res) => {
  try {
    const response = await fetch("https://api.wolver002.com/map/players");
    const data = await response.json();
    res.json(data);
  } catch (err) {
    res.status(500).json({
      error: "Failed to fetch players",
      details: err.message
    });
  }
});

// IMPORTANT: Render needs this
const PORT = process.env.PORT || 3000;

app.listen(PORT, () => {
  console.log("Server running on port", PORT);
});
