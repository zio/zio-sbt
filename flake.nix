{
  description = "Node.js 24.22.1 (prebuilt binary)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };

        nodejs_24 = pkgs.stdenv.mkDerivation rec {
          pname = "nodejs";
          version = "24.22.1";

          src = pkgs.fetchurl {
            url = "https://nodejs.org/dist/v${version}/node-v${version}-linux-x64.tar.xz";
            sha256 = "sha256-bBcqmrZxj3uAH7WnvUhq8u5eMnWAhqBLhPzOKqdwleY=";
          };

          nativeBuildInputs = [ pkgs.autoPatchelfHook ];
          buildInputs = [ pkgs.stdenv.cc.cc.lib ];

          installPhase = ''
            mkdir -p $out
            cp -r ./* $out/
          '';
        };
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [ nodejs_24 ];
        };

        packages.default = nodejs_24;
      }
    );
}
