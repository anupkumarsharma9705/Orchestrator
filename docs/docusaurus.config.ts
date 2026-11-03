import { themes as prismThemes } from 'prism-react-renderer';
import type { Config } from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Java Companion Orchestrator',
  tagline: 'Generate secure Spring Boot REST APIs from natural language',
  favicon: 'img/favicon.ico',

  // Set the production url of your site here
  url: 'https://akash.github.io',
  // Set the /<baseUrl>/ pathname under which your site is served
  baseUrl: '/java-companion/',

  // GitHub pages deployment config
  organizationName: 'akash',
  projectName: 'java-companion',
  trailingSlash: false,

  onBrokenLinks: 'throw',
  markdown: {
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/akash/java-companion/edit/main/docs/',
          remarkPlugins: [
            [require('@docusaurus/remark-plugin-npm2yarn'), { sync: true }],
          ],
        },
        blog: {
          showReadingTime: true,
          feedOptions: {
            type: ['rss', 'atom'],
            xslt: true,
          },
          editUrl: 'https://github.com/akash/java-companion/edit/main/docs/',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/social-card.png',
    navbar: {
      title: 'Companion',
      logo: {
        alt: 'Companion Logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: 'Docs',
        },
        { to: '/blog', label: 'Blog', position: 'left' },
        {
          href: 'https://github.com/akash/java-companion',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            { label: 'Quick Start', to: '/docs/quick-start' },
            { label: 'Installation', to: '/docs/installation' },
            { label: 'CLI Reference', to: '/docs/cli-reference' },
            { label: 'Offline Mode', to: '/docs/offline-mode' },
          ],
        },
        {
          title: 'Community',
          items: [
            { label: 'GitHub Discussions', href: 'https://github.com/akash/java-companion/discussions' },
            { label: 'Issues', href: 'https://github.com/akash/java-companion/issues' },
          ],
        },
        {
          title: 'More',
          items: [
            { label: 'Blog', to: '/blog' },
            { label: 'GitHub', href: 'https://github.com/akash/java-companion' },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Java Companion Orchestrator. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'bash', 'yaml', 'json'],
    },
    algolia: {
      appId: 'YOUR_ALGOLIA_APP_ID',
      apiKey: 'YOUR_ALGOLIA_API_KEY',
      indexName: 'java-companion',
    },
  } satisfies Preset.ThemeConfig,

  scripts: [
    {
      src: 'https://buttons.github.io/buttons.js',
      async: true,
      defer: true,
    },
  ],
};

export default config;