const express = require('express');

const app = express();

app.use(express.static(__dirname));

// PLAYER API PROXY
app.get('/players', async (req, res) => {

  try {

    const response = await fetch(
      'https://factionsvr.com/api/map/players'
    );

    const data = await response.json();

    res.json(data);

  } catch (err) {

    console.error(err);

    res.status(500).json({
      error: 'Failed to load players'
    });

  }

});

app.listen(3000, () => {
  console.log('Server running on http://localhost:3000');
});