<?php
// CrazeEngine privacy-first search homepage.
// Search execution will be connected to the backend in the next stage.
$query = isset($_GET['q']) ? trim((string) $_GET['q']) : '';
$query = mb_substr($query, 0, 200, 'UTF-8');
?>
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="no-referrer">
<title>CrazeEngine</title>
<style>
*{box-sizing:border-box}body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#fff;color:#202124;font-family:Arial,sans-serif}.wrap{width:min(680px,92vw);text-align:center}.logo{font-size:46px;font-weight:700;letter-spacing:-2px;margin-bottom:30px}.search{display:flex;border:1px solid #dfe1e5;border-radius:28px;padding:6px 8px 6px 18px;box-shadow:0 1px 5px rgba(0,0,0,.08)}input{flex:1;border:0;outline:0;font-size:17px;background:transparent;color:inherit}.btn{border:0;border-radius:22px;padding:11px 20px;background:#202124;color:#fff;font-weight:600;cursor:pointer}.privacy{margin-top:22px;font-size:13px;color:#70757a}@media(prefers-color-scheme:dark){body{background:#202124;color:#e8eaed}.search{border-color:#5f6368}.privacy{color:#9aa0a6}}
</style>
</head>
<body>
<main class="wrap">
<div class="logo">CrazeEngine</div>
<form class="search" action="index.php" method="get" autocomplete="off">
<input name="q" maxlength="200" value="<?= htmlspecialchars($query, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') ?>" placeholder="Search the web" aria-label="Search the web" autofocus>
<button class="btn" type="submit">Search</button>
</form>
<div class="privacy">Privacy-first search · No account · No search history</div>
</main>
</body>
</html>
