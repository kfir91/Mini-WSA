<?php
/**
 * Dev-only: skip the Adminer login form and connect to Mini WSA Postgres.
 * Credentials come from container env (see docker-compose adminer service).
 */
class AdminerAutologin
{
    private function cfgServer(): string
    {
        return getenv('ADMINER_DEFAULT_SERVER') ?: 'postgres';
    }

    private function cfgUsername(): string
    {
        $user = getenv('ADMINER_USERNAME');
        if ($user === false || $user === '') {
            $user = getenv('POSTGRES_USER') ?: 'miniwsa';
        }
        return $user;
    }

    private function cfgPassword(): string
    {
        $pass = getenv('ADMINER_PASSWORD');
        if ($pass === false || $pass === '') {
            $pass = getenv('POSTGRES_PASSWORD');
        }
        return $pass === false ? '' : $pass;
    }

    private function cfgDatabase(): string
    {
        $db = getenv('ADMINER_DB');
        if ($db === false || $db === '') {
            $db = getenv('POSTGRES_DB') ?: 'miniwsa';
        }
        return $db;
    }

    function credentials()
    {
        return [$this->cfgServer(), $this->cfgUsername(), $this->cfgPassword()];
    }

    function database()
    {
        return $this->cfgDatabase();
    }

    function login($login, $password)
    {
        return true;
    }

    function loginForm()
    {
        $server = htmlspecialchars($this->cfgServer(), ENT_QUOTES, 'UTF-8');
        $user = htmlspecialchars($this->cfgUsername(), ENT_QUOTES, 'UTF-8');
        $pass = htmlspecialchars($this->cfgPassword(), ENT_QUOTES, 'UTF-8');
        $db = htmlspecialchars($this->cfgDatabase(), ENT_QUOTES, 'UTF-8');
        echo <<<HTML
<style>form[action]{display:none!important}body{background:#1a2332;color:#9aa8bc;font-family:system-ui,sans-serif;padding:2rem}</style>
<p>Connecting to PostgreSQL…</p>
<form id="miniwsa-autologin" action="" method="post">
  <input type="hidden" name="auth[driver]" value="pgsql">
  <input type="hidden" name="auth[server]" value="{$server}">
  <input type="hidden" name="auth[username]" value="{$user}">
  <input type="hidden" name="auth[password]" value="{$pass}">
  <input type="hidden" name="auth[db]" value="{$db}">
</form>
<script>document.getElementById('miniwsa-autologin').submit();</script>
HTML;
    }
}

return new AdminerAutologin();
