export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'header-max-length': [2, 'always', 72],
  },
  helpUrl: 'Expected format: type(scope): description. See docs/agil/branching.md',
};
