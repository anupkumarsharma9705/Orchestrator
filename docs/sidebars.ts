// @ts-check
/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  tutorialSidebar: [
    'intro',
    {
      type: 'category',
      label: 'Getting Started',
      items: [
        'quick-start',
        'installation',
        'configuration',
        'offline-mode',
      ],
    },
    {
      type: 'category',
      label: 'CLI Reference',
      items: [
        'cli-reference',
      ],
    },
    {
      type: 'category',
      label: 'Architecture',
      items: [
        'architecture/overview',
        'architecture/modules',
        'architecture/security',
        'architecture/deployment',
      ],
    },
    {
      type: 'category',
      label: 'Advanced',
      items: [
        'advanced/troubleshooting',
      ],
    },
    {
      type: 'category',
      label: 'Contributing',
      items: [
        'contributing/development',
      ],
    },
  ],
};

export default sidebars;