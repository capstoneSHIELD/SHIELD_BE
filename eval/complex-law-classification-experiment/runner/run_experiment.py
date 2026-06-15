from __future__ import annotations

import argparse
import sys

from shield_experiment.config import ExperimentConfig
from shield_experiment.pipeline import ExperimentPipelineFacade


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run SHIELD legal classification and matching experiment.")
    parser.add_argument("--config", required=True, help="Path to JSON config. YAML requires PyYAML.")
    args = parser.parse_args(argv)

    config = ExperimentConfig.from_file(args.config)
    context = ExperimentPipelineFacade().run(config)
    print(f"run_id={context.run_id}")
    print(f"output_dir={context.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
