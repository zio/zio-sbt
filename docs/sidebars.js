const sidebars = {
  sidebar: [
    {
      type: "doc",
      id: "index",
      label: "Introduction"
    },
    {
      type: "doc",
      id: "getting-started",
      label: "Getting Started"
    },
    {
      type: "category",
      label: "Concepts",
      collapsed: false,
      link: { type: "doc", id: "concepts/index" },
      items: [
        "concepts/how-the-plugins-fit-together",
        "concepts/generated-files"
      ]
    },
    {
      type: "category",
      label: "Reference",
      collapsed: false,
      link: { type: "doc", id: "reference/index" },
      items: [
        "reference/ecosystem",
        "reference/website",
        "reference/ci",
        "reference/githubactions",
        "reference/source",
        "reference/scaladoc"
      ]
    },
    {
      type: "doc",
      id: "tutorials/set-up-a-new-zio-library",
      label: "Tutorial"
    },
    {
      type: "category",
      label: "How-to Guides",
      collapsed: true,
      items: [
        "how-to-guides/publish-a-releases-docs-again",
        "how-to-guides/auto-approve-and-auto-merge-dependency-update-prs",
        "how-to-guides/regenerate-workflows-on-bot-prs",
        "how-to-guides/enable-netlify-deploy-previews",
        "how-to-guides/draft-releases-with-release-drafter",
        "how-to-guides/configure-dependabot-updates",
        "how-to-guides/configure-scala-steward",
        "how-to-guides/keep-the-workflow-in-sync",
        "how-to-guides/customize-the-generated-ci-jobs",
        "how-to-guides/build-a-workflow-of-only-custom-jobs",
        "how-to-guides/set-job-timeouts",
        "how-to-guides/control-ci-concurrency",
        "how-to-guides/add-a-service-container-to-a-job",
        "how-to-guides/change-the-workflow-environment",
        "how-to-guides/test-against-several-scala-and-java-versions",
        "how-to-guides/check-scaladoc-examples-in-ci",
        "how-to-guides/customize-the-generated-readme"
      ]
    }
  ]
};

module.exports = sidebars;
