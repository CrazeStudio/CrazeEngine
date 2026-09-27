import unittest

from backend.security import validate_url


class SecurityTests(unittest.TestCase):
    def test_https_is_allowed_for_public_host(self):
        self.assertEqual(validate_url("https://example.com/"), "https://example.com/")

    def test_localhost_is_blocked(self):
        with self.assertRaises(ValueError):
            validate_url("http://localhost/")

    def test_loopback_ip_is_blocked(self):
        with self.assertRaises(ValueError):
            validate_url("http://127.0.0.1/")

    def test_private_ip_is_blocked(self):
        with self.assertRaises(ValueError):
            validate_url("http://192.168.1.1/")

    def test_non_http_schemes_are_blocked(self):
        for url in ("file:///etc/passwd", "ftp://example.com/file", "javascript:alert(1)"):
            with self.assertRaises(ValueError):
                validate_url(url)

    def test_oversized_url_is_blocked(self):
        with self.assertRaises(ValueError):
            validate_url("https://example.com/" + "a" * 3000)


if __name__ == "__main__":
    unittest.main()
